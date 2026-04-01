package net.portswigger.mcp.config.components

import net.portswigger.mcp.config.*
import net.portswigger.mcp.security.findBurpFrame
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.*
import javax.swing.*
import javax.swing.JOptionPane.*

class AllowedOriginsPanel(
    private val config: McpConfig,
    private val onOriginsChanged: () -> Unit
) : JPanel() {

    private var listenerHandle: ListenerHandle? = null
    private var refreshListener: (() -> Unit)? = null

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        updateColors()
        alignmentX = LEFT_ALIGNMENT

        buildPanel()
    }

    override fun updateUI() {
        super.updateUI()
        updateColors()
    }

    private fun updateColors() {
        background = Design.Colors.surface
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Design.Colors.outlineVariant, 1),
            BorderFactory.createEmptyBorder(Design.Spacing.MD, Design.Spacing.MD, Design.Spacing.MD, Design.Spacing.MD)
        )
    }

    private fun buildPanel() {
        add(Design.createSectionLabel("Allowed Origin Hosts"))
        add(Box.createVerticalStrut(Design.Spacing.MD))

        val descLabel = JLabel(
            "<html>Additional hosts allowed to connect to the MCP server.<br>" +
                "localhost and 127.0.0.1 are always allowed.</html>"
        ).apply {
            alignmentX = LEFT_ALIGNMENT
            font = Design.Typography.bodyMedium
            foreground = Design.Colors.onSurfaceVariant
            border = BorderFactory.createEmptyBorder(0, 0, Design.Spacing.SM, 0)
        }
        val examplesLabel = JLabel("Examples: host.docker.internal, 192.168.1.100, mydevbox.local").apply {
            alignmentX = LEFT_ALIGNMENT
            font = Design.Typography.labelMedium
            foreground = Design.Colors.onSurfaceVariant
            border = BorderFactory.createEmptyBorder(0, 0, Design.Spacing.SM, 0)
        }

        val warningLabel = WarningLabel(
            "Adding origin hosts weakens DNS rebinding protection. Only add hosts you trust."
        )

        add(descLabel)
        add(examplesLabel)
        add(warningLabel)
        add(Box.createVerticalStrut(Design.Spacing.SM))

        val listModel = DefaultListModel<String>()
        val originsList = createOriginsList(listModel)
        updateOriginsList(listModel)

        refreshListener = {
            SwingUtilities.invokeLater {
                updateOriginsList(listModel)
            }
        }
        listenerHandle = config.addAllowedOriginsChangeListener(refreshListener!!)

        val scrollPane = createScrollPane(originsList)
        val tableContainer = createTableContainer(scrollPane)
        add(tableContainer)

        val buttonsPanel = createButtonsPanel(originsList, listModel)
        add(buttonsPanel)
    }

    private fun createOriginsList(listModel: DefaultListModel<String>): JList<String> {
        return object : JList<String>(listModel) {
            private var rolloverIndex = -1

            init {
                selectionMode = ListSelectionModel.SINGLE_SELECTION
                visibleRowCount = 5
                font = Design.Typography.bodyMedium
                background = Design.Colors.listBackground
                foreground = Design.Colors.onSurface
                border = BorderFactory.createEmptyBorder(
                    Design.Spacing.SM, Design.Spacing.MD, Design.Spacing.SM, Design.Spacing.MD
                )
                cellRenderer = createCellRenderer()
                addMouseMotionListener(createMouseMotionListener())
                addMouseListener(createMouseListener())
                addKeyListener(createKeyListener(listModel))
                isFocusable = true
            }

            private fun createCellRenderer() = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
                ): Component {
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                    border = BorderFactory.createEmptyBorder(
                        Design.Spacing.SM, Design.Spacing.MD, Design.Spacing.SM, Design.Spacing.MD
                    )

                    val isRollover = index == rolloverIndex && !isSelected

                    when {
                        isSelected -> {
                            background = Design.Colors.listSelectionBackground
                            foreground = Design.Colors.listSelectionForeground
                        }

                        isRollover -> {
                            background = Design.Colors.listHoverBackground
                            foreground = Design.Colors.onSurface
                        }

                        else -> {
                            background =
                                if (index % 2 == 0) Design.Colors.listBackground else Design.Colors.listAlternatingBackground
                            foreground = Design.Colors.onSurface
                        }
                    }
                    return this
                }
            }

            private fun createMouseMotionListener() = object : MouseMotionAdapter() {
                override fun mouseMoved(e: MouseEvent) {
                    try {
                        val index = locationToIndex(e.point)
                        val newRolloverIndex = if (index >= 0 && index < model.size && getCellBounds(
                                index, index
                            )?.contains(e.point) == true
                        ) {
                            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                            index
                        } else {
                            cursor = Cursor.getDefaultCursor()
                            -1
                        }

                        if (rolloverIndex != newRolloverIndex) {
                            rolloverIndex = newRolloverIndex
                            repaint()
                        }
                    } catch (_: Exception) {
                        rolloverIndex = -1
                        cursor = Cursor.getDefaultCursor()
                    }
                }
            }

            private fun createMouseListener() = object : MouseAdapter() {
                override fun mouseExited(e: MouseEvent) {
                    if (rolloverIndex != -1) {
                        rolloverIndex = -1
                        cursor = Cursor.getDefaultCursor()
                        repaint()
                    }
                }
            }

            private fun createKeyListener(listModel: DefaultListModel<String>) = object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    when (e.keyCode) {
                        KeyEvent.VK_DELETE, KeyEvent.VK_BACK_SPACE -> {
                            if (selectedIndex >= 0 && selectedIndex < model.size) {
                                try {
                                    removeOrigin(selectedIndex, listModel)
                                    e.consume()
                                } catch (ex: Exception) {
                                    ex.printStackTrace()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun createScrollPane(originsList: JList<String>): JScrollPane {
        return JScrollPane(originsList).apply {
            val baseHeight = 220
            val baseWidth = 400
            val scaleFactor = Design.Spacing.MD / 16f
            val responsiveHeight = (baseHeight * scaleFactor).toInt().coerceAtLeast(150)
            val responsiveWidth = (baseWidth * scaleFactor).toInt().coerceAtLeast(250)

            maximumSize = Dimension(Int.MAX_VALUE, responsiveHeight)
            preferredSize = Dimension(responsiveWidth, responsiveHeight)
            minimumSize = Dimension((responsiveWidth * 0.625f).toInt(), (responsiveHeight * 0.68f).toInt())
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Design.Colors.listBorder, 1), BorderFactory.createEmptyBorder(1, 1, 1, 1)
            )
            background = Design.Colors.listBackground
            viewport.background = Design.Colors.listBackground
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        }
    }

    private fun createTableContainer(scrollPane: JScrollPane): JPanel {
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            alignmentX = LEFT_ALIGNMENT
            border = BorderFactory.createEmptyBorder(0, 0, Design.Spacing.MD, 0)
            add(scrollPane)
        }
    }

    private fun createButtonsPanel(originsList: JList<String>, listModel: DefaultListModel<String>): JPanel {
        val buttonsPanel = JPanel(FlowLayout(FlowLayout.LEFT, Design.Spacing.SM, Design.Spacing.SM)).apply {
            isOpaque = false
            alignmentX = LEFT_ALIGNMENT
            border = BorderFactory.createEmptyBorder(Design.Spacing.SM, 0, 0, 0)
        }

        val addButton = Design.createFilledButton("Add").apply {
            addActionListener {
                val input = Dialogs.showInputDialog(
                    findBurpFrame(),
                    "Enter origin hostname to allow:\nExamples: host.docker.internal, 192.168.1.100, mydevbox.local"
                )

                if (!input.isNullOrBlank()) {
                    val trimmed = input.trim()
                    if (OriginHostValidation.isValidOriginHost(trimmed)) {
                        addOrigin(trimmed)
                    } else {
                        Dialogs.showMessageDialog(
                            findBurpFrame(),
                            "Invalid hostname format. Use a hostname or IP address (e.g. host.docker.internal, 192.168.1.100).",
                            ERROR_MESSAGE
                        )
                    }
                }
            }
        }

        val removeButton = Design.createOutlinedButton("Remove").apply {
            addActionListener {
                val selectedIndex = originsList.selectedIndex
                if (selectedIndex >= 0) {
                    removeOrigin(selectedIndex, listModel)
                }
            }
        }

        val clearButton = Design.createOutlinedButton("Clear All").apply {
            addActionListener {
                val result = Dialogs.showConfirmDialog(
                    findBurpFrame(), "Remove all allowed origin hosts?", YES_NO_OPTION
                )

                if (result == YES_OPTION) {
                    clearAllOrigins()
                }
            }
        }

        buttonsPanel.add(addButton)
        buttonsPanel.add(removeButton)
        buttonsPanel.add(clearButton)

        return buttonsPanel
    }

    private fun updateOriginsList(listModel: DefaultListModel<String>) {
        listModel.clear()
        config.getAllowedOriginHostsList().forEach {
            listModel.addElement(it)
        }
    }

    private fun addOrigin(host: String) {
        config.addAllowedOriginHost(host)
        onOriginsChanged()
    }

    private fun removeOrigin(index: Int, listModel: DefaultListModel<String>) {
        if (index >= 0 && index < listModel.size()) {
            val host = listModel.getElementAt(index)
            config.removeAllowedOriginHost(host)
            onOriginsChanged()
        }
    }

    private fun clearAllOrigins() {
        config.clearAllowedOriginHosts()
        onOriginsChanged()
    }

    fun cleanup() {
        listenerHandle?.remove()
        listenerHandle = null
        refreshListener = null
    }
}
